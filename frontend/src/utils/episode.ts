/** "S#E#" prefix used everywhere an episode is listed. `seasonNumber` is `undefined` while still loading (see
 * `useSeasonNumbers`) -- falls back to just "E#" rather than blocking the whole row on it. */
export function episodeCode(seasonNumber: number | undefined, episodeNumber: number): string {
  return seasonNumber === undefined ? `E${episodeNumber}` : `S${seasonNumber}E${episodeNumber}`
}
