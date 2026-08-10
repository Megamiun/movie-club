import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type RatingFillWith = 'number' | 'description' | 'none'

interface RatingDisplaySettings {
  gradientPercent: number
  fillWith: RatingFillWith
}

interface RatingDisplayContextValue extends RatingDisplaySettings {
  setGradientPercent: (value: number) => void
  setFillWith: (value: RatingFillWith) => void
}

const STORAGE_KEY = 'movie-club-rating-display'
const DEFAULTS: RatingDisplaySettings = { gradientPercent: 20, fillWith: 'number' }

function loadSettings(): RatingDisplaySettings {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return DEFAULTS
  try {
    const parsed = JSON.parse(raw)
    return {
      gradientPercent: typeof parsed.gradientPercent === 'number' ? parsed.gradientPercent : DEFAULTS.gradientPercent,
      fillWith: parsed.fillWith === 'description' || parsed.fillWith === 'none' ? parsed.fillWith : 'number',
    }
  } catch {
    return DEFAULTS
  }
}

const RatingDisplayContext = createContext<RatingDisplayContextValue | null>(null)

/** Purely a personal display preference (like a theme toggle), not club data -- kept in localStorage rather than
 * on Member server-side, since it doesn't need to be shared with or visible to anyone else. */
export function RatingDisplayProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<RatingDisplaySettings>(loadSettings)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  }, [settings])

  const value = useMemo<RatingDisplayContextValue>(
    () => ({
      ...settings,
      setGradientPercent: (gradientPercent) => setSettings((s) => ({ ...s, gradientPercent })),
      setFillWith: (fillWith) => setSettings((s) => ({ ...s, fillWith })),
    }),
    [settings],
  )

  return <RatingDisplayContext.Provider value={value}>{children}</RatingDisplayContext.Provider>
}

export function useRatingDisplay() {
  const ctx = useContext(RatingDisplayContext)
  if (!ctx) throw new Error('useRatingDisplay must be used within RatingDisplayProvider')
  return ctx
}
