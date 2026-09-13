import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { DateDisplayStyle } from '../utils/date'

const STORAGE_KEY = 'movie-club-date-display-style'

function loadDateDisplayStyle(): DateDisplayStyle {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'iso' ? 'iso' : 'compact'
  } catch {
    return 'compact'
  }
}

interface DateDisplayContextValue {
  dateStyle: DateDisplayStyle
  setDateStyle: (value: DateDisplayStyle) => void
}

const DateDisplayContext = createContext<DateDisplayContextValue | null>(null)

/** How a meeting's date renders across the app (Meetings table, Watchlist's meeting picker, meeting detail,
 * Calendar) -- 'compact' ("13 Sep 2026", the new default) or 'iso' (the raw "2026-09-13" wire format, the old
 * default). A personal display preference like `RatingDisplayContext`/`MemberPhotoContext`, so `localStorage`-
 * persisted rather than club data -- there's no per-club "house style" for this. */
export function DateDisplayProvider({ children }: { children: ReactNode }) {
  const [dateStyle, setDateStyleState] = useState(loadDateDisplayStyle)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, dateStyle)
  }, [dateStyle])

  const value = useMemo<DateDisplayContextValue>(
    () => ({ dateStyle, setDateStyle: setDateStyleState }),
    [dateStyle],
  )

  return <DateDisplayContext.Provider value={value}>{children}</DateDisplayContext.Provider>
}

export function useDateDisplay() {
  const ctx = useContext(DateDisplayContext)
  if (!ctx) throw new Error('useDateDisplay must be used within DateDisplayProvider')
  return ctx
}
