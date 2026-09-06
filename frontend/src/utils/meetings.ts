/** Orders a list of meetings for a picker so the most likely target is easy to find rather than scrolling a long
 * chronological list: the 4 closest to [referenceDate] (either direction) first, then everyone else chronologically.
 * Shared by the meeting detail page's swap/merge picker (referenceDate = that meeting's own date, excluding itself)
 * and the Watchlist's move-to-meeting picker (referenceDate = today, nothing to exclude). */
export function orderMeetingsByProximity<T extends { id: string; date: string }>(
  meetings: T[],
  referenceDate: string,
  excludeId?: string,
): T[] {
  const others = excludeId ? meetings.filter((m) => m.id !== excludeId) : meetings
  const referenceTime = new Date(referenceDate).getTime()
  const byDistance = [...others].sort(
    (a, b) => Math.abs(new Date(a.date).getTime() - referenceTime) - Math.abs(new Date(b.date).getTime() - referenceTime),
  )
  const closest = byDistance.slice(0, 4)
  const closestIds = new Set(closest.map((m) => m.id))
  const rest = others.filter((m) => !closestIds.has(m.id)).sort((a, b) => a.date.localeCompare(b.date))
  return [...closest, ...rest]
}
