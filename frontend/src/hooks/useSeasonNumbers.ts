import { seasonsApi } from '../api/series'
import { useAsync } from './useAsync'

/** Resolves the `S#` half of an "S#E#" episode prefix for a set of episodes that may span several seasons/series
 * (e.g. a meeting's episode picks) -- `Episode` itself only carries `seasonId`, not the season's own `number`, so
 * this fetches each distinct season once and returns a lookup keyed by `seasonId`. */
export function useSeasonNumbers(seasonIds: string[]) {
  const distinctIds = [...new Set(seasonIds)].sort()
  const key = distinctIds.join(',')

  const { data } = useAsync(async () => {
    if (distinctIds.length === 0) return new Map<string, number>()
    const seasons = await Promise.all(distinctIds.map((id) => seasonsApi.get(id)))
    return new Map(seasons.map((season) => [season.id, season.number]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  return data
}
