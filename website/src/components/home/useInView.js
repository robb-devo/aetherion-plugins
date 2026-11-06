import { useEffect, useRef, useState } from 'react'

/** True while the element is on screen. Modules use it to start, and to stop costing anything. */
export function useInView({ margin = '0px', once = false, threshold = 0 } = {}) {
  const ref = useRef(null)
  const [inView, setInView] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setInView(true)
          if (once) io.disconnect()
        } else if (!once) setInView(false)
      },
      { rootMargin: margin, threshold },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [margin, once, threshold])
  return [ref, inView]
}
