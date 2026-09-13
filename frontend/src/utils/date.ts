export type DateDisplayStyle = 'compact' | 'iso'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** Formats an ISO `YYYY-MM-DD` meeting date per the viewer's own `DateDisplayContext` preference -- 'compact'
 * ("13 Sep 2026") or 'iso' (the raw string, unchanged). Parses the string's own digits directly rather than
 * `new Date(isoDate)` + `toLocaleDateString` -- a bare `YYYY-MM-DD` parses as UTC midnight, which `toLocaleDateString`
 * would then render as the previous day in any negative-UTC-offset timezone. */
export function formatMeetingDate(isoDate: string, style: DateDisplayStyle): string {
  if (style === 'iso') return isoDate
  const [year, month, day] = isoDate.split('-')
  const monthName = MONTHS[Number(month) - 1] ?? month
  // Zero-padded ("05" not "5") so the day is always 2 characters -- combined with rendering in a monospace font
  // (see MeetingsPage's `DATE_TEXT_SX`), this keeps the day/month/year segments the same width on every row, so a
  // column of dates lines up like a table even though it's really just one text string per cell.
  return `${day.padStart(2, '0')} ${monthName} ${year}`
}

function startOfWeek(date: Date): number {
  const dayIndex = (date.getUTCDay() + 6) % 7 // Monday = 0 .. Sunday = 6
  const start = new Date(date)
  start.setUTCDate(date.getUTCDate() - dayIndex)
  return start.getTime()
}

/** True when [isoDate] falls in the same Monday-Sunday calendar week as today -- used to flag "this week's meeting"
 * in the Meetings table, since there's no server-side "current meeting" concept to key off (meetings happen
 * roughly weekly, but the schedule is just a plain list of dates). */
export function isCurrentWeek(isoDate: string): boolean {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  const today = new Date()
  const todayUtc = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()))
  return startOfWeek(date) === startOfWeek(todayUtc)
}
