import { AnimatePresence, LayoutGroup, motion, useMotionValue, useReducedMotion, useScroll, useSpring, useTransform } from 'framer-motion'
import { useEffect, useRef, useState } from 'react'
import { HERO_IMAGE } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { easeOut, easeToll, scrollSpring } from '../../motion.js'
import { BrandMark } from '../Brand.jsx'
import { FogSheet } from '../fx/Fog.jsx'
import Particles from '../fx/Particles.jsx'
import { JoinLinks, ServerEntry, VanillaServerEntry } from './Join.jsx'

// Join pieces moved to Join.jsx; re-exported so older imports keep working.
export { DownloadIcon, JoinDock, JoinLinks, JoinSteps, LiveStatus, ServerEntry, VanillaServerEntry } from './Join.jsx'

const INTRO_KEY = 'aetherion-intro-seen'
const MIN_IGNITE = 1150
const MAX_WAIT = 2600

function introAlreadySeen() {
  try {
    return window.sessionStorage.getItem(INTRO_KEY) === '1'
  } catch {
    return true
  }
}

/**
 * The hero, as a title sequence.
 *
 * 1. Ignite — the page opens on void. The A assembles: tide half, amethyst
 *    half, the island rising beneath, the seam of bell light last. This covers
 *    the hero image loading instead of showing grey mush.
 * 2. Reveal — once the island has arrived (or 2.6 s pass), the A flies to its
 *    place above the name, a shockwave rolls out, the fog curtains part, the
 *    name settles out of wide tracking.
 * 3. Live — the isle drifts and leans toward the cursor, fog slides past in
 *    two layers, light shafts breathe, aether motes rise and shy from the
 *    pointer.
 *
 * Any click, key or scroll skips straight to 2. Once per session; reduced
 * motion gets a still frame.
 */
export default function Hero() {
  const reduced = useReducedMotion()
  const { copy } = useLang()
  const ref = useRef(null)
  const imgRef = useRef(null)
  const [playIntro] = useState(() => !reduced && !introAlreadySeen())
  const [phase, setPhase] = useState(playIntro ? 'ignite' : 'reveal')
  const [imageReady, setImageReady] = useState(false)

  // Reveal when the island has loaded and the A has had its moment — or when the visitor is impatient.
  useEffect(() => {
    if (phase !== 'ignite') return undefined
    const started = performance.now()
    let done = false
    const reveal = () => {
      if (done) return
      done = true
      setPhase('reveal')
    }
    const check = window.setInterval(() => {
      const elapsed = performance.now() - started
      if ((imageReady && elapsed > MIN_IGNITE) || elapsed > MAX_WAIT) reveal()
    }, 80)
    const skip = () => reveal()
    window.addEventListener('pointerdown', skip)
    window.addEventListener('keydown', skip)
    window.addEventListener('wheel', skip, { passive: true })
    window.addEventListener('touchmove', skip, { passive: true })
    return () => {
      window.clearInterval(check)
      window.removeEventListener('pointerdown', skip)
      window.removeEventListener('keydown', skip)
      window.removeEventListener('wheel', skip)
      window.removeEventListener('touchmove', skip)
    }
  }, [phase, imageReady])

  useEffect(() => {
    if (phase === 'reveal') {
      try {
        window.sessionStorage.setItem(INTRO_KEY, '1')
      } catch {
        // storage blocked: the intro simply plays again next time
      }
    }
  }, [phase])

  useEffect(() => {
    if (imgRef.current?.complete) setImageReady(true)
  }, [])

  const live = phase === 'reveal'
  // The curtains are only needed until they've parted; after that they'd just cost frames.
  const [curtainsGone, setCurtainsGone] = useState(false)
  useEffect(() => {
    if (!live) return undefined
    const id = window.setTimeout(() => setCurtainsGone(true), 3600)
    return () => window.clearTimeout(id)
  }, [live])
  const { scrollYProgress: raw } = useScroll({ target: ref, offset: ['start start', 'end start'] })
  const scrollYProgress = useSpring(raw, scrollSpring)
  const imageY = useTransform(scrollYProgress, [0, 1], ['0%', reduced ? '0%' : '18%'])
  const imageScale = useTransform(scrollYProgress, [0, 1], [1, reduced ? 1 : 1.1])
  const contentY = useTransform(scrollYProgress, [0, 1], ['0%', reduced ? '0%' : '-18%'])
  // The name and the join box leave before the voyage's title arrives, so the two never overlap.
  const contentOpacity = useTransform(scrollYProgress, [0, 0.42], [1, reduced ? 1 : 0])
  // 'none' until the fade is underway: any filter here would cut the join box's backdrop blur.
  const contentBlur = useTransform(scrollYProgress, (v) => (reduced || v < 0.12 ? 'none' : `blur(${Math.min(6, ((v - 0.12) / 0.3) * 6).toFixed(2)}px)`))

  // Pointer: the isle leans toward the cursor, the fog more, and a lantern follows it.
  const px = useMotionValue(0)
  const py = useMotionValue(0)
  const sx = useSpring(px, { stiffness: 40, damping: 18, mass: 1 })
  const sy = useSpring(py, { stiffness: 40, damping: 18, mass: 1 })
  const isleX = useTransform(sx, (v) => v * -16)
  const isleY = useTransform(sy, (v) => v * -12)
  const fogX = useTransform(sx, (v) => v * 40)
  const lanternX = useTransform(sx, (v) => `${(v + 0.5) * 100}%`)
  const lanternY = useTransform(sy, (v) => `${(v + 0.5) * 100}%`)

  function onPointerMove(event) {
    if (reduced || event.pointerType !== 'mouse') return
    const rect = event.currentTarget.getBoundingClientRect()
    px.set((event.clientX - rect.left) / rect.width - 0.5)
    py.set((event.clientY - rect.top) / rect.height - 0.5)
  }

  const after = (delay, from = { opacity: 0, y: 16 }) =>
    reduced
      ? {}
      : { initial: from, animate: live ? { opacity: 1, y: 0 } : from, transition: { duration: 0.95, delay: live ? delay : 0, ease: easeOut } }

  return (
    <LayoutGroup>
      <section
        id="top"
        ref={ref}
        onPointerMove={onPointerMove}
        className="relative isolate flex min-h-[100svh] flex-col overflow-hidden px-4 pt-24 pb-10 sm:px-6 sm:pb-12"
      >
        {/* The island */}
        <motion.div className="absolute inset-0 -z-30" style={{ y: imageY, scale: imageScale }} aria-hidden="true">
          <motion.div className="absolute -inset-[5%]" style={{ x: isleX, y: isleY }}>
            <motion.img
              ref={imgRef}
              src={HERO_IMAGE}
              alt=""
              onLoad={() => setImageReady(true)}
              className="h-full w-full object-cover object-[50%_34%] sm:object-[50%_38%]"
              initial={reduced ? false : { scale: 1.14, opacity: 0 }}
              animate={reduced ? { opacity: 1 } : live ? { scale: 1, opacity: 1, y: ['0%', '-1.2%', '0%'] } : { scale: 1.14, opacity: 0 }}
              transition={
                reduced
                  ? { duration: 0 }
                  : {
                      scale: { duration: 3, ease: easeOut },
                      opacity: { duration: 1.4, ease: easeOut },
                      y: { duration: 12, ease: 'easeInOut', repeat: Infinity, delay: 3 },
                    }
              }
              fetchPriority="high"
              draggable="false"
            />
          </motion.div>
        </motion.div>

        {/* Grade: dusk-dim, night tint, the islands' three lights, dusk, rays */}
        <div className="absolute inset-0 -z-20 bg-[#05070c]/15" aria-hidden="true" />
        <div className="hero-tint absolute inset-0 -z-20" aria-hidden="true" />
        <div className="arcane-grade absolute inset-0 -z-20" aria-hidden="true" />
        <div className="hero-dusk absolute inset-0 -z-20" aria-hidden="true" />
        <div className="god-rays -z-20" aria-hidden="true" />
        <motion.div className="lantern absolute inset-0 -z-20 hidden md:block" style={{ '--lx': lanternX, '--ly': lanternY }} aria-hidden="true" />

        {/* Drifting fog, two depths, leaning with the pointer */}
        <motion.div className="pointer-events-none absolute inset-x-0 bottom-[-6%] -z-10 h-[62%]" style={{ x: fogX }} aria-hidden="true">
          <FogSheet texture="a" tint="aether" width={1700} duration={120} opacity={0.42} />
          <FogSheet texture="b" tint="mist" width={1150} duration={75} reverse opacity={0.34} />
        </motion.div>

        {/* Fog curtains: closed over the isle until the reveal, then drawn aside */}
        <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden="true">
          {reduced || curtainsGone ? null : (
            <>
              <motion.div
                className="absolute inset-y-[-10%] left-[-12%] w-[80%] [mask-image:linear-gradient(90deg,#000_55%,transparent)] [-webkit-mask-image:linear-gradient(90deg,#000_55%,transparent)]"
                initial={{ x: '14%', opacity: 1 }}
                animate={live ? { x: '-62%', opacity: 0.2 } : { x: '14%', opacity: 1 }}
                transition={{ duration: 3.2, ease: easeOut }}
              >
                <FogSheet texture="a" tint="mist" width={1300} duration={60} opacity={1} />
              </motion.div>
              <motion.div
                className="absolute inset-y-[-10%] right-[-12%] w-[80%] [mask-image:linear-gradient(270deg,#000_55%,transparent)] [-webkit-mask-image:linear-gradient(270deg,#000_55%,transparent)]"
                initial={{ x: '-14%', opacity: 1 }}
                animate={live ? { x: '62%', opacity: 0.2 } : { x: '-14%', opacity: 1 }}
                transition={{ duration: 3.4, ease: easeOut }}
              >
                <FogSheet texture="b" tint="mist" width={1100} duration={50} reverse opacity={1} />
              </motion.div>
            </>
          )}
          {live && !reduced ? (
            <>
              <motion.span
                className="shock-ring"
                style={{ top: '44%' }}
                initial={{ scale: 0.2, opacity: 0 }}
                animate={{ scale: 5.2, opacity: [0, 0.95, 0] }}
                transition={{ duration: 2.8, delay: 0.25, ease: easeToll }}
              />
              <motion.span
                className="bell-ring"
                style={{ top: '44%' }}
                initial={{ scale: 0.15, opacity: 0 }}
                animate={{ scale: 3.6, opacity: [0, 0.7, 0] }}
                transition={{ duration: 3.2, delay: 0.6, ease: easeToll }}
              />
            </>
          ) : null}
        </div>

        <Particles palette="aether" count={90} mode="rise" speed={0.8} size={[2.5, 7]} interactive className="-z-10" />

        <motion.div
          className="relative mx-auto mt-auto flex w-full max-w-6xl flex-col items-center text-center"
          style={{ y: contentY, opacity: contentOpacity, filter: contentBlur }}
        >
          {/* The A lands here */}
          <div className="h-[clamp(3.6rem,8vw,6.4rem)] w-[clamp(3.6rem,8vw,6.4rem)]">
            {phase !== 'ignite' ? (
              <motion.div layoutId="brand-a" className="h-full w-full" transition={{ type: 'spring', stiffness: 70, damping: 16 }}>
                <motion.div
                  className="brand-glow h-full w-full"
                  animate={reduced ? undefined : { y: [0, -7, 0] }}
                  transition={{ duration: 6, ease: 'easeInOut', repeat: Infinity, delay: 1.2 }}
                >
                  <BrandMark className="h-full w-full" assemble={!playIntro && !reduced} delay={0.1} />
                </motion.div>
              </motion.div>
            ) : null}
          </div>

          <motion.h1
            className="wordmark wordmark-sheen mt-3 text-[clamp(2.7rem,13vw,4.6rem)] sm:mt-4 sm:text-[clamp(4.6rem,10.6vw,10.5rem)]"
            initial={reduced ? false : { opacity: 0, letterSpacing: '0.42em', filter: 'blur(10px)' }}
            animate={reduced ? undefined : live ? { opacity: 1, letterSpacing: '0.06em', filter: 'blur(0px)' } : { opacity: 0 }}
            transition={{ duration: 1.7, delay: live ? 0.3 : 0, ease: easeOut }}
          >
            <span className="pl-[0.06em]">AETHERION</span>
          </motion.h1>

          <motion.p
            className="font-lore mt-3 max-w-[38ch] text-balance text-[clamp(1.15rem,2.2vw,1.65rem)] leading-snug text-parchment [text-shadow:0_2px_18px_rgba(3,5,9,0.95)] sm:mt-4"
            {...after(0.9)}
          >
            {copy.hero.lore}
          </motion.p>

          <motion.div className="mt-7 w-full max-w-[40rem] sm:mt-8" {...after(1.15, { opacity: 0, y: 24 })}>
            <ServerEntry />
            <VanillaServerEntry />
            <JoinLinks className="mt-3" />
          </motion.div>
        </motion.div>
      </section>

      {/* Ignition: the void the A assembles in, while the island loads */}
      <AnimatePresence>
        {phase === 'ignite' ? (
          <motion.div
            key="ignite"
            className="fixed inset-0 z-[60] grid place-items-center bg-void"
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.9, ease: easeOut } }}
            aria-hidden="true"
          >
            <div className="absolute inset-0 bg-[radial-gradient(ellipse_50%_45%_at_50%_52%,rgba(155,92,255,0.18),transparent_70%),radial-gradient(ellipse_35%_30%_at_50%_60%,rgba(46,230,214,0.12),transparent_70%)]" />
            <Particles palette="aether" count={50} mode="rise" speed={1.1} size={[2, 6]} />
            <motion.div layoutId="brand-a" className="relative h-[34vmin] w-[34vmin]" transition={{ type: 'spring', stiffness: 70, damping: 16 }}>
              <div className="brand-glow h-full w-full">
                <BrandMark className="h-full w-full" assemble delay={0.1} />
              </div>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </LayoutGroup>
  )
}
