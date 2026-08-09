import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'

interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null })

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true, error: null }))
    fn()
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((err) =>
        setState({ data: null, loading: false, error: err instanceof ApiError ? err.message : 'Something went wrong' }),
      )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  /** Refetches without ever touching `loading` -- unlike `reload`, this never causes an `AsyncState` wrapper to
   * swap its children for a spinner, so it's safe to call from a background poll or right after a small mutation
   * (e.g. saving one rating) without disrupting scroll position or any currently-open popover. A failed background
   * refresh is silently dropped rather than surfaced, since whatever's already on screen is still valid. */
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const silentReload = useCallback(() => {
    fn()
      .then((data) => setState((s) => ({ ...s, data, error: null })))
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    reload()
  }, [reload])

  return { ...state, reload, silentReload }
}
