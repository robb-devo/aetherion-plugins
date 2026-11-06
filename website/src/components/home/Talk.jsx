import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { BANTER, BARKS } from '../../data/cast.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'
import { NpcFace, NpcFigure } from './Npc.jsx'
import { useInView } from './useInView.js'

const CPS = 58 // characters per second: how fast the game's bubbles write

/** Writes a line character by character; a tap finishes it at once. */
function useTypewriter(text, onDone) {
  const [count, setCount] = useState(0)
  const done = useRef(onDone)
  done.current = onDone
  useEffect(() => {
    setCount(0)
    if (!text) return undefined
    const start = performance.now()
    let raf = 0
    const tick = (now) => {
      const n = Math.min(text.length, Math.floor(((now - start) / 1000) * CPS))
      setCount(n)
      if (n < text.length) raf = requestAnimationFrame(tick)
      else done.current?.()
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [text])
  return text ? text.slice(0, count) : ''
}

function Egon({ t, you, inView }) {
  const fill = useCallback((s) => s.replaceAll('{player}', you), [you])
  const [queue, setQueue] = useState([])
  const [line, setLine] = useState(null)
  const [typed, setTyped] = useState(false)
  const [seen, setSeen] = useState(() => new Set())
  const [unlocked, setUnlocked] = useState(() => new Set())
  const [quest, setQuest] = useState(false)
  const started = useRef(false)
  const idleTimer = useRef(0)
  const text = useTypewriter(line, () => setTyped(true))

  const say = useCallback(
    (lines) => {
      setTyped(false)
      setLine(fill(lines[0]))
      setQueue(lines.slice(1).map(fill))
    },
    [fill],
  )

  // He speaks first, once you are near.
  useEffect(() => {
    if (inView && !started.current) {
      started.current = true
      const id = setTimeout(() => say([...t.greet, ...t.intro]), 500)
      return () => clearTimeout(id)
    }
    return undefined
  }, [inView, say, t])

  // Next bubble in the same breath; a pause long enough to read.
  useEffect(() => {
    if (!typed || !queue.length) return undefined
    const wait = Math.min(1600, 550 + (line?.length ?? 0) * 14)
    const id = setTimeout(() => {
      setTyped(false)
      setLine(queue[0])
      setQueue((q) => q.slice(1))
    }, wait)
    return () => clearTimeout(id)
  }, [typed, queue, line])

  // Left alone, he mutters.
  useEffect(() => {
    window.clearTimeout(idleTimer.current)
    if (!inView || !typed || queue.length) return undefined
    idleTimer.current = window.setTimeout(() => {
      const pool = t.idle
      if (pool.length) say([pool[Math.floor(Math.random() * pool.length)]])
    }, 16000)
    return () => window.clearTimeout(idleTimer.current)
  }, [inView, typed, queue, say, t.idle, line])

  // A tap finishes the line, a second tap moves on: nobody has to wait for a bubble.
  const finish = () => {
    if (!line) return
    if (!typed) setTyped(true)
    else if (queue.length) {
      setTyped(false)
      setLine(queue[0])
      setQueue((q) => q.slice(1))
    }
  }

  const pick = (chip) => {
    say(chip.say)
    setSeen((s) => new Set(s).add(chip.id))
    if (chip.then) setUnlocked((s) => new Set(s).add(chip.then))
    if (chip.quest) setQuest(true)
  }

  const reset = () => {
    setSeen(new Set())
    setUnlocked(new Set())
    setQuest(false)
    say([...t.greet, ...t.intro])
  }

  const chipsReady = line && !queue.length
  const chips = t.chips.filter((c) => (!c.hidden || unlocked.has(c.id)) && !seen.has(c.id) && !(quest && c.quest) && !(quest && c.end))
  const talking = Boolean(line) && !typed
  const shown = typed ? line : text

  return (
    <div className="egon">
      <div className="egon-stage">
        <button type="button" className="bubble" aria-live="polite" onClick={finish} tabIndex={-1}>
          <span className="bubble-name">{t.name}</span>
          <span className="bubble-text">
            {shown}
            <span className="bubble-ghost" aria-hidden="true">
              {line ? line.slice(shown.length) : ''}
            </span>
          </span>
        </button>
        <div className="egon-figure">
          <NpcFigure id="egon" px={7} talking={talking} />
          <span className="pier" aria-hidden="true" />
        </div>
      </div>
      <div className="egon-side">
        {quest ? <QuestToast /> : null}
        <div className="chips" role="group">
          {chipsReady
            ? chips.map((chip) => (
                <button type="button" key={chip.id} className="chip" onClick={() => pick(chip)}>
                  <img src={asset('ui/mark_quest.png')} alt="" className="chip-mark" aria-hidden="true" />
                  {chip.label}
                </button>
              ))
            : null}
          {chipsReady && !chips.length ? (
            <button type="button" className="chip chip-again" onClick={reset}>
              {t.again}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function QuestToast() {
  const { copy } = useLang()
  const q = copy.home.talk.quest
  return (
    <div className="quest-toast" role="status">
      <img src={asset('items/quest_book.png')} alt="" className="pixelated" />
      <div>
        <p className="quest-added">{q.added}</p>
        <p className="quest-name">{q.name}</p>
        <p className="quest-body">{q.body}</p>
      </div>
    </div>
  )
}

/** People on the harbour talking to each other. Real banter, in the game's order of exchange. */
function Harbour({ lang, t, inView }) {
  const scenes = useMemo(() => {
    const out = []
    const b = BARKS.map((x) => [x])
    // weave: an exchange, then a couple of mutterers
    let bi = 0
    for (let i = 0; i < BANTER.length; i += 1) {
      out.push(BANTER[i])
      out.push(b[bi++ % b.length])
      out.push(b[bi++ % b.length])
    }
    while (bi < b.length) out.push(b[bi++])
    return out
  }, [])
  const [scene, setScene] = useState(0)
  const [beat, setBeat] = useState(0)
  const [hold, setHold] = useState(false)
  const current = scenes[scene]

  useEffect(() => {
    if (!inView || hold) return undefined
    const lineLen = current[beat][lang].length
    const wait = Math.max(1700, 700 + lineLen * 34)
    const id = setTimeout(() => {
      if (beat < current.length - 1) setBeat(beat + 1)
      else {
        setScene((scene + 1) % scenes.length)
        setBeat(0)
      }
    }, wait)
    return () => clearTimeout(id)
  }, [inView, hold, beat, scene, current, lang, scenes.length])

  const people = useMemo(() => BARKS.slice(0, 14), [])
  return (
    <div className="harbour" onPointerEnter={() => setHold(true)} onPointerLeave={() => setHold(false)}>
      <p className="harbour-label">{t.castLabel}</p>
      <ul className="harbour-lines" aria-live="off">
        {current.slice(0, beat + 1).map((b, i) => (
          <li key={`${scene}-${i}`} className={`harbour-line ${current.length > 1 && i % 2 ? 'harbour-line-b' : ''}`}>
            <NpcFace id={b.id} size={40} />
            <p>
              <span className="harbour-who">{b.name[lang]}</span>
              {b[lang]}
            </p>
          </li>
        ))}
      </ul>
      <div className="harbour-faces" role="group" aria-label={t.tapHint}>
        {people.map((p) => (
          <button
            type="button"
            key={p.id}
            className="harbour-face"
            aria-label={p.name[lang]}
            onClick={() => {
              setScene(scenes.findIndex((s) => s.length === 1 && s[0].id === p.id))
              setBeat(0)
            }}
          >
            <NpcFace id={p.id} size={32} />
          </button>
        ))}
      </div>
    </div>
  )
}

export default function Talk() {
  const { copy, lang } = useLang()
  const t = copy.home.talk
  const [ref, inView] = useInView({ margin: '-10% 0px' })
  return (
    <Chapter id="talk" tone="day" className="ch-talk">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div ref={ref} className="talk-grid" key={lang}>
        <Egon t={t.egon} you={t.you} inView={inView} />
        <Harbour lang={lang} t={t} inView={inView} />
      </div>
    </Chapter>
  )
}
