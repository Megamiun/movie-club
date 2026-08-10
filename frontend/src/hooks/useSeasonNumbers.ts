import { seasonsApi } from '../api/series'
import { useAsync } from './useAsync'

export interface SeasonCodeInfo {
  number: number
  /** Digit width of the largest season number across the whole series (this season's own siblings) -- used to
   * zero-pad the "S#" half of an "S#E#" code. */
  seasonDigits: number
  /** Digit width of the largest episode number within this specific season -- used to zero-pad the "E#" half. */
  episodeDigits: number
}

/** Digit width needed to display up to [max] without truncation (e.g. `digitsOf(130)` is 3, for zero-padding a
 * code like "E007"..."E130" to a consistent width) -- shared by `SeasonDetailPage`, which already has its episode
 * list in-page and derives this directly rather than going through this hook. */
export const digitsOf = (max: number) => Math.max(max, 1).toString().length

const seasonCache = new Map<string, SeasonCodeInfo>()
const pendingPromises = new Map<string, Promise<SeasonCodeInfo>>()

async function fetchSeasonCodeInfo(seasonId: string): Promise<SeasonCodeInfo> {
  if (seasonCache.has(seasonId)) return seasonCache.get(seasonId)!
  if (pendingPromises.has(seasonId)) return pendingPromises.get(seasonId)!

  const promise = (async () => {
    try {
      const [siblings, episodes] = await Promise.all([
        seasonsApi.listSiblings(seasonId),
        seasonsApi.listEpisodes(seasonId),
      ])
      const thisSeason = siblings.find((s) => s.id === seasonId)
      const info: SeasonCodeInfo = {
        number: thisSeason?.number ?? 0,
        seasonDigits: digitsOf(Math.max(0, ...siblings.map((s) => s.number))),
        episodeDigits: digitsOf(Math.max(0, ...episodes.map((e) => e.number))),
      }
      seasonCache.set(seasonId, info)
      return info
    } finally {
      pendingPromises.delete(seasonId)
    }
  })()

  pendingPromises.set(seasonId, promise)
  return promise
}

/** Resolves everything an "S#E#" code needs for a set of episodes that may span several seasons/series (e.g. a
 * meeting's episode picks) -- `Episode` itself only carries `seasonId`, not the season's own `number` or either
 * digit width, so this fetches each distinct season's siblings (for the season-number width) and episode list
 * (for the episode-number width) once and returns a lookup keyed by `seasonId`. Cached across polls. */
export function useSeasonNumbers(seasonIds: string[]) {
  const distinctIds = [...new Set(seasonIds)].sort()
  const key = distinctIds.join(',')

  const { data } = useAsync(async () => {
    if (distinctIds.length === 0) return new Map<string, SeasonCodeInfo>()
    const entries = await Promise.all(
      distinctIds.map(async (seasonId): Promise<[string, SeasonCodeInfo]> => {
        const info = await fetchSeasonCodeInfo(seasonId)
        return [seasonId, info]
      }),
    )
    return new Map(entries)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  return data
}
