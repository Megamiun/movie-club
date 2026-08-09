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

const digitsOf = (max: number) => Math.max(max, 1).toString().length

/** Resolves everything an "S#E#" code needs for a set of episodes that may span several seasons/series (e.g. a
 * meeting's episode picks) -- `Episode` itself only carries `seasonId`, not the season's own `number` or either
 * digit width, so this fetches each distinct season's siblings (for the season-number width) and episode list
 * (for the episode-number width) once and returns a lookup keyed by `seasonId`. */
export function useSeasonNumbers(seasonIds: string[]) {
  const distinctIds = [...new Set(seasonIds)].sort()
  const key = distinctIds.join(',')

  const { data } = useAsync(async () => {
    if (distinctIds.length === 0) return new Map<string, SeasonCodeInfo>()
    const entries = await Promise.all(
      distinctIds.map(async (seasonId): Promise<[string, SeasonCodeInfo]> => {
        const [siblings, episodes] = await Promise.all([
          seasonsApi.listSiblings(seasonId),
          seasonsApi.listEpisodes(seasonId),
        ])
        const thisSeason = siblings.find((s) => s.id === seasonId)
        return [
          seasonId,
          {
            number: thisSeason?.number ?? 0,
            seasonDigits: digitsOf(Math.max(0, ...siblings.map((s) => s.number))),
            episodeDigits: digitsOf(Math.max(0, ...episodes.map((e) => e.number))),
          },
        ]
      }),
    )
    return new Map(entries)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  return data
}
