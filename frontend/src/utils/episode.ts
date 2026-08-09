/** "S#E#" prefix used everywhere an episode is listed. `seasonNumber` is `undefined` while still loading (see
 * `useSeasonNumbers`) -- falls back to just "E#" rather than blocking the whole row on it. `seasonDigits`/
 * `episodeDigits`, when known, zero-pad each half to that width (e.g. `S03E07` in a series with 12 seasons and a
 * season with 20+ episodes) so codes line up; omitted (or while still loading) leaves that half unpadded. */
export function episodeCode(
  seasonNumber: number | undefined,
  episodeNumber: number,
  seasonDigits?: number,
  episodeDigits?: number,
): string {
  const e = episodeDigits ? String(episodeNumber).padStart(episodeDigits, '0') : String(episodeNumber)
  if (seasonNumber === undefined) return `E${e}`
  const s = seasonDigits ? String(seasonNumber).padStart(seasonDigits, '0') : String(seasonNumber)
  return `S${s}E${e}`
}
