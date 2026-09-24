import { useEffect, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { api, errorMessage } from '../../lib/api.js'
import { ACTIVE_STATUSES } from '../../lib/format.js'
import { usePolling } from '../../lib/usePolling.js'
import { Spinner, useToast } from '../ui.jsx'

function lineTone(line) {
  if (/\b(ERROR|SEVERE|FATAL)\b|Exception/.test(line)) return 'text-redstone'
  if (/\bWARN(ING)?\b/.test(line)) return 'text-gold'
  if (/Done \(|\bjoined the game\b|\blogged in\b/.test(line)) return 'text-emerald'
  return 'text-mist/80'
}

function Line({ text }) {
  const match = /^(\[[^\]]{4,40}\]:?\s*)(.*)$/.exec(text)
  return (
    <div className={`console-line ${lineTone(text)}`}>
      {match ? (
        <>
          <span className="text-white/25">{match[1]}</span>
          {match[2]}
        </>
      ) : (
        text
      )}
    </div>
  )
}

export default function ConsolePanel({ server, username }) {
  const { copy } = useLang()
  const t = copy.app.dash.console
  const showToast = useToast()
  const running = ACTIVE_STATUSES.has(server.status)
  const [command, setCommand] = useState('')
  const [sending, setSending] = useState(false)
  const [follow, setFollow] = useState(true)
  const history = useRef([])
  const historyIndex = useRef(-1)
  const scroller = useRef(null)

  const { data, refresh } = usePolling(() => api.console(server.id, 300), {
    interval: running ? 2000 : 12000,
    deps: [server.id],
  })
  const lines = data?.lines ?? []

  useEffect(() => {
    if (running) void refresh()
  }, [running, refresh])

  useEffect(() => {
    const box = scroller.current
    if (box && follow) box.scrollTop = box.scrollHeight
  }, [data, follow])

  async function send(event) {
    event.preventDefault()
    const value = command.trim()
    if (!value || sending) return
    setSending(true)
    try {
      await api.command(server.id, value)
      history.current = [value, ...history.current.filter((item) => item !== value)].slice(0, 30)
      historyIndex.current = -1
      setCommand('')
      setFollow(true)
      window.setTimeout(refresh, 400)
    } catch (error) {
      showToast(errorMessage(error, copy), 'error')
    } finally {
      setSending(false)
    }
  }

  function onKeyDown(event) {
    if (event.key === 'ArrowUp' && history.current.length) {
      event.preventDefault()
      historyIndex.current = Math.min(history.current.length - 1, historyIndex.current + 1)
      setCommand(history.current[historyIndex.current])
    } else if (event.key === 'ArrowDown' && historyIndex.current >= 0) {
      event.preventDefault()
      historyIndex.current -= 1
      setCommand(historyIndex.current >= 0 ? history.current[historyIndex.current] : '')
    }
  }

  return (
    <div>
      <div className="relative">
        <div
          ref={scroller}
          className="console h-[26rem] overflow-y-auto p-4"
          role="log"
          aria-live="off"
          tabIndex={0}
          onScroll={(event) => {
            const box = event.currentTarget
            setFollow(box.scrollHeight - box.scrollTop - box.clientHeight < 40)
          }}
        >
          {lines.length ? lines.map((line, index) => <Line key={`${index}-${line.slice(0, 24)}`} text={line} />) : (
            <p className="text-ash">{running ? t.empty : t.offline}</p>
          )}
        </div>
        {!follow && lines.length ? (
          <button
            type="button"
            className="btn btn-secondary btn-sm notch absolute right-4 bottom-4"
            onClick={() => setFollow(true)}
          >
            ↓ {t.follow}
          </button>
        ) : null}
      </div>
      <form className="mt-3 flex gap-2" onSubmit={send}>
        <div className="relative flex-1">
          <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 font-mono text-sm text-amethyst" aria-hidden="true">
            /
          </span>
          <input
            className="field pl-7 font-mono text-sm"
            placeholder={running ? interpolate(t.placeholder, { name: username }) : t.offline}
            value={command}
            maxLength={240}
            disabled={!running}
            aria-label={interpolate(t.placeholder, { name: username })}
            onKeyDown={onKeyDown}
            onChange={(event) => setCommand(event.target.value)}
          />
        </div>
        <button type="submit" className="btn btn-secondary notch" disabled={!running || !command.trim() || sending}>
          {sending ? <Spinner /> : null}
          {t.send}
        </button>
      </form>
    </div>
  )
}
