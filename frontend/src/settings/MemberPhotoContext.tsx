import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

const STORAGE_KEY = 'movie-club-show-member-photos'

function loadShowPhotos(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

interface MemberPhotoContextValue {
  showPhotos: boolean
  setShowPhotos: (value: boolean) => void
}

const MemberPhotoContext = createContext<MemberPhotoContextValue | null>(null)

/** Whether `MemberBadge` shows a member's uploaded photo in place of their colored initials -- a personal display
 * preference like `RatingDisplayContext`, not club data, so it's `localStorage`-persisted rather than server-side.
 * Defaults to off: initials are the same fixed size/shape everywhere already, while a member's photo may or may
 * not even be set, so defaulting to "on" would make the meetings table/watchlist look inconsistent (some cells
 * photos, some initials) for any club that hasn't had every member upload one yet. */
export function MemberPhotoProvider({ children }: { children: ReactNode }) {
  const [showPhotos, setShowPhotosState] = useState(loadShowPhotos)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, String(showPhotos))
  }, [showPhotos])

  const value = useMemo<MemberPhotoContextValue>(
    () => ({ showPhotos, setShowPhotos: setShowPhotosState }),
    [showPhotos],
  )

  return <MemberPhotoContext.Provider value={value}>{children}</MemberPhotoContext.Provider>
}

export function useMemberPhotos() {
  const ctx = useContext(MemberPhotoContext)
  if (!ctx) throw new Error('useMemberPhotos must be used within MemberPhotoProvider')
  return ctx
}
