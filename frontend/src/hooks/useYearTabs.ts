import { useState } from 'react'

/** Groups any date-bearing list into year tabs -- shared by `MeetingsPage` and `CalendarPage`, which both show the
 * same underlying meetings sorted newest-tab-first but as a different layout (table rows vs. a poster grid).
 * Defaults to the current calendar year, or the most recent year with any items if the current year has none yet;
 * `setSelectedYear` is exposed so a caller can also jump to a specific year itself (e.g. `MeetingsPage` switching
 * to the year of a meeting it just created). */
export function useYearTabs<T extends { date: string }>(items: T[]) {
  const [selectedYear, setSelectedYear] = useState<string | null>(null)

  const sorted = [...items].sort((a, b) => a.date.localeCompare(b.date))
  const years = [...new Set(sorted.map((item) => item.date.slice(0, 4)))].sort((a, b) => b.localeCompare(a))
  const currentYear = String(new Date().getFullYear())
  const defaultYear = years.includes(currentYear) ? currentYear : (years.at(0) ?? currentYear)
  const effectiveYear = selectedYear && years.includes(selectedYear) ? selectedYear : defaultYear
  const itemsForYear = sorted.filter((item) => item.date.slice(0, 4) === effectiveYear)

  return { sorted, years, currentYear, effectiveYear, itemsForYear, setSelectedYear }
}
