import { useEffect, useState } from 'react'
import { SITE, VANILLA_SERVER } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { useLauncherDownload } from '../../lib/hooks.js'
import { Link } from '../../router.jsx'
import { CopyButton } from '../ui.jsx'
import { DownloadIcon, VanillaRow } from './Server.jsx'

export default function Join() {
  const { copy } = useLang()
  const t = copy.home.join
  const launcher = useLauncherDownload()
  return (
    <>
      <section id="join" data-chapter="join" data-mood="dark" className="join">
        <div className="join-in">
          <h2 className="beat-title">{t.title}</h2>
          <ol className="join-steps">
            <li>
              <h3>{t.steps[0].title}</h3>
              <p>{interpolate(t.steps[0].body, SITE_VARS)}</p>
              <a href={launcher} className="btn btn-secondary notch join-act">
                <DownloadIcon />
                {copy.hero.launcher}
              </a>
            </li>
            <li>
              <h3>{t.steps[1].title}</h3>
              <p>{t.steps[1].body}</p>
              <CopyButton value={SITE.ip} copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle} className="btn btn-primary notch join-act">
                {SITE.ip}
              </CopyButton>
            </li>
            <li>
              <h3>{t.steps[2].title}</h3>
              <p>{t.steps[2].body}</p>
            </li>
          </ol>
          <h3 className="join-more">{t.more}</h3>
          <ul className="join-links">
            <li>
              <Link to="/wiki">{copy.nav.wiki}</Link>
              <span>{t.wiki}</span>
            </li>
            <li>
              <Link to="/playground">{copy.nav.playground}</Link>
              <span>{t.playground}</span>
            </li>
            <li>
              <a href={SITE.discord} target="_blank" rel="noreferrer">
                Discord
              </a>
              <span>{t.discord}</span>
            </li>
            <li>
              <Link to="/support">{copy.nav.support}</Link>
              <span>{t.support}</span>
            </li>
            {VANILLA_SERVER.join ? (
              <li>
                <span className="join-links-v">{VANILLA_SERVER.name}</span>
                <span>{t.vanilla}</span>
              </li>
            ) : null}
          </ul>
          <VanillaRow className="join-vanilla" />
        </div>
      </section>
      <section id="finale" data-chapter="finale" className="finale">
        <p className="finale-title">{copy.home.finale.title}</p>
      </section>
    </>
  )
}

/** Phones: once the hero is behind you, the address rides along at the bottom of the screen. */
export function JoinDock() {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const onScroll = () => {
      const past = window.scrollY > window.innerHeight * 0.9
      // the join section has the address itself: the dock steps aside for it
      const join = document.getElementById('join')
      const end = join ? join.getBoundingClientRect().top < window.innerHeight * 0.7 : false
      setVisible(past && !end)
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [])

  return (
    <div className={`dock ${visible ? 'dock-on' : ''}`} role="region" aria-label={copy.dock.label} aria-hidden={!visible} inert={!visible}>
      <CopyButton value={SITE.ip} copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle} className="btn btn-primary notch dock-ip">
        <span className="truncate">{SITE.ip}</span>
        <span className="dock-ip-act">{copy.dock.copy}</span>
      </CopyButton>
      <a href={launcher} className="btn btn-secondary notch dock-dl" aria-label={copy.hero.launcher} title={copy.hero.launcher}>
        <DownloadIcon />
      </a>
    </div>
  )
}
