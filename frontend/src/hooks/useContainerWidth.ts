import { useCallback, useRef, useState } from 'react'

/** Tracks a ref'd element's content-box width via ResizeObserver -- lets a caller compute a layout (e.g. how many
 * fixed-size grid items fit per row) that reacts to viewport/sidebar changes, not just the initial render. A
 * callback ref, not an object ref + mount-effect: the tracked element is typically behind a loading conditional
 * (e.g. rendered only once async data arrives), so an effect with `[]` deps would find `ref.current` still null at
 * the one moment it runs and never attach the observer at all. The callback ref instead fires exactly when the
 * element actually mounts/unmounts, whenever that happens to be. */
export function useContainerWidth<T extends HTMLElement>() {
  const observerRef = useRef<ResizeObserver | null>(null)
  const [width, setWidth] = useState(0)

  const ref = useCallback((el: T | null) => {
    observerRef.current?.disconnect()
    observerRef.current = null
    if (!el) return

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry) setWidth(entry.contentRect.width)
    })
    observer.observe(el)
    observerRef.current = observer
  }, [])

  return [ref, width] as const
}
