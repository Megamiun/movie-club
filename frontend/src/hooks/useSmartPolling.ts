import { useEffect, useRef } from 'react'

/**
 * Periodically invokes [onPoll] every [intervalMs] (defaulting to 15 seconds), but automatically pauses
 * polling when the browser tab is hidden or inactive (`document.hidden`), and immediately triggers a fresh
 * poll as soon as the user returns to the tab.
 */
export function useSmartPolling(onPoll: () => void, intervalMs: number = 15000) {
  const savedOnPoll = useRef(onPoll)

  useEffect(() => {
    savedOnPoll.current = onPoll
  }, [onPoll])

  useEffect(() => {
    let timerId: ReturnType<typeof setInterval> | null = null

    const startTimer = () => {
      if (timerId === null) {
        timerId = setInterval(() => {
          if (!document.hidden) {
            savedOnPoll.current()
          }
        }, intervalMs)
      }
    }

    const stopTimer = () => {
      if (timerId !== null) {
        clearInterval(timerId)
        timerId = null
      }
    }

    const handleVisibilityChange = () => {
      if (document.hidden) {
        stopTimer()
      } else {
        savedOnPoll.current()
        startTimer()
      }
    }

    if (!document.hidden) {
      startTimer()
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      stopTimer()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [intervalMs])
}
