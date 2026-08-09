/** Minutes are zero-padded to 2 digits, but only alongside an "h" part (e.g. "1h05m") -- a bare "05m" with no
 * hours would read oddly, and there's no adjacent "h1m"/"h45m" pair it needs to line up with. */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  if (hours === 0) return `${remaining}m`
  if (remaining === 0) return `${hours}h`
  return `${hours}h${String(remaining).padStart(2, '0')}m`
}
