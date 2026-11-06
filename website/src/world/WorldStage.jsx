import { useEffect, useRef } from 'react'
import { useRouter } from '../router.jsx'
import { stage } from './stage.js'

/**
 * The fixed layers behind every page. They sit directly in the root stacking context, so the home
 * page's title can slide between the sky and the city. Keep every ancestor free of transforms,
 * filters, z-index and isolation.
 */
export default function WorldStage() {
  const { path } = useRouter()
  const refs = useRef({})
  const set = (name) => (el) => {
    refs.current[name] = el
  }

  useEffect(() => {
    stage.attach(refs.current)
  }, [])

  useEffect(() => {
    stage.setActive(path === '/')
  }, [path])

  return (
    <div aria-hidden="true">
      <span ref={set('lvh')} className="w-lvh" />
      <canvas ref={set('sky')} className="w-sky" width="2" height="256" />
      <div ref={set('stars')} className="w-stars" />
      <div ref={set('sun')} className="w-sun" />
      <div ref={set('moon')} className="w-moon" />
      <canvas ref={set('canvas')} className="w-canvas" />
      <div ref={set('veil')} className="w-veil" />
    </div>
  )
}
